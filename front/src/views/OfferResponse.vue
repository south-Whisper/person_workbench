<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPublicOffer, respondToPublicOffer } from '../api/candidate'
import BrandLockup from '@/components/BrandLockup.vue'

const route = useRoute()
const token = computed(() => String(route.query.token || ''))
const offer = ref(null), loading = ref(true), saving = ref(false), error = ref(''), reason = ref('')
const finished = computed(() => ['已接受', '已拒绝'].includes(offer.value?.responseStatus))
const salary = computed(() => offer.value?.salaryMode === 'annual' ? `${offer.value?.annualSalary || '—'} K / 年` : `${offer.value?.actualSalary || '—'} K / 月 × ${offer.value?.salaryMonths || '—'} 薪`)
const probation = computed(() => Number(offer.value?.probationMonths) ? `${offer.value.probationMonths} 个月` : '无试用期')

async function load() {
  loading.value = true; error.value = ''
  try { if (!token.value) throw new Error('Offer 链接不完整'); offer.value = await getPublicOffer(token.value) }
  catch (e) { error.value = e.message || 'Offer 无法读取' }
  finally { loading.value = false }
}
async function respond(decision) {
  if (saving.value || finished.value || offer.value?.expired) return
  if (decision === 'DECLINED' && !reason.value.trim()) { error.value = '拒绝时请填写原因，方便 HR 了解情况'; return }
  saving.value = true; error.value = ''
  try { offer.value = await respondToPublicOffer(token.value, { decision, reason: reason.value.trim() }) }
  catch (e) { error.value = e.message || '回复提交失败，请稍后重试' }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <main class="offer-response-page">
    <section v-if="loading" class="offer-card state-card">正在读取 Offer…</section>
    <section v-else-if="error && !offer" class="offer-card state-card error"><h1>暂时无法打开</h1><p>{{ error }}</p></section>
    <section v-else class="offer-card">
      <header><BrandLockup icon-only /><div><small>OFFER LETTER</small><h1>{{ offer.company }}</h1></div></header>
      <div class="welcome"><small>候选人</small><h2>{{ offer.personName }}，您好</h2><p>我们诚邀您加入，担任 <strong>{{ offer.jobName }}</strong>。</p></div>
      <dl><div><dt>岗位</dt><dd>{{ offer.jobName }}</dd></div><div><dt>薪资方案</dt><dd>{{ salary }}</dd></div><div><dt>试用期</dt><dd>{{ probation }}</dd></div><div><dt>社保与公积金</dt><dd>{{ offer.socialInsurance }}</dd></div><div><dt>计划入职</dt><dd>{{ offer.expectedStartDate }}</dd></div><div><dt>有效期至</dt><dd>{{ offer.expiresAt }}</dd></div><div><dt>负责 HR</dt><dd>{{ offer.owner }}</dd></div></dl>
      <div v-if="finished" class="result" :class="offer.responseStatus === '已接受' ? 'accepted' : 'declined'"><h3>{{ offer.responseStatus === '已接受' ? '您已接受这份 Offer' : '您已拒绝这份 Offer' }}</h3><p>回复已送达负责 HR。{{ offer.respondedAt ? `提交时间：${String(offer.respondedAt).replace('T', ' ').slice(0, 16)}` : '' }}</p><p v-if="offer.responseReason">说明：{{ offer.responseReason }}</p></div>
      <div v-else-if="offer.expired" class="result declined"><h3>这份 Offer 已过有效期</h3><p>请联系负责 HR 重新确认。</p></div>
      <form v-else @submit.prevent><label>如需拒绝，请填写原因<textarea v-model.trim="reason" maxlength="1000" placeholder="接受 Offer 时可以不填；拒绝时必须填写" rows="3" /></label><p v-if="error" class="inline-error" role="alert">{{ error }}</p><div class="actions"><button type="button" class="decline" :disabled="saving" @click="respond('DECLINED')">拒绝 Offer</button><button type="button" class="accept" :disabled="saving" @click="respond('ACCEPTED')">{{ saving ? '正在提交…' : '接受 Offer' }}</button></div></form>
      <footer>此页面是您的专属回复入口，请勿转发链接。</footer>
    </section>
  </main>
</template>

<style scoped>
.offer-response-page{min-height:100vh;display:grid;place-items:center;padding:32px 16px;background:linear-gradient(145deg,#eef4ff,#f8fafc);font-family:Inter,"Microsoft YaHei",sans-serif;color:#334155}.offer-card{width:min(680px,100%);box-sizing:border-box;padding:32px;border:1px solid #dbe5f2;border-radius:22px;background:#fff;box-shadow:0 24px 70px rgba(45,73,112,.14)}header{display:flex;align-items:center;gap:14px;padding-bottom:22px;border-bottom:1px solid #e7edf5}.brand{display:grid;place-items:center;width:44px;height:44px;border-radius:13px;background:#3474ed;color:#fff;font-size:22px;font-weight:700}header small{color:#3474ed;letter-spacing:2px;font-size:10px}header h1{margin:5px 0 0;font-size:17px}.welcome{padding:26px 0 18px}.welcome small{color:#94a3b8}.welcome h2{margin:7px 0;font-size:25px}.welcome p{color:#64748b}dl{overflow:hidden;margin:0;border-radius:14px;background:#e7edf4}dl div{display:grid;grid-template-columns:140px 1fr;gap:12px;padding:13px 16px;background:#f8fafc;border-bottom:1px solid #e7edf4}dl div:last-child{border:0}dt{color:#94a3b8;font-size:13px}dd{margin:0;font-weight:600}form{margin-top:22px}label{display:grid;gap:8px;color:#64748b;font-size:13px}textarea{box-sizing:border-box;width:100%;padding:12px;border:1px solid #d7e0eb;border-radius:11px;resize:vertical;font:inherit}.actions{display:flex;justify-content:flex-end;gap:10px;margin-top:16px}.actions button{padding:11px 22px;border-radius:10px;font-weight:600;cursor:pointer}.decline{border:1px solid #d7e0eb;background:#fff;color:#64748b}.accept{border:0;background:#3474ed;color:#fff;box-shadow:0 8px 20px #3474ed35}.actions button:disabled{opacity:.55;cursor:wait}.inline-error{padding:10px 12px;border-radius:9px;background:#fff1f2;color:#be123c}.result{margin-top:22px;padding:18px;border-radius:13px}.result h3{margin:0 0 7px}.result p{margin:5px 0;font-size:13px}.accepted{background:#ecfdf5;color:#047857}.declined{background:#fff1f2;color:#be123c}footer{margin-top:23px;text-align:center;color:#a0aec0;font-size:11px}.state-card{text-align:center}.state-card.error{color:#be123c}@media(max-width:520px){.offer-card{padding:22px}dl div{grid-template-columns:110px 1fr}.actions{display:grid;grid-template-columns:1fr 1fr}.actions button{padding:11px 8px}}
</style>
