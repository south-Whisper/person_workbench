<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getPublicOffer, respondToPublicOffer } from '../api/candidate'
import BrandLockup from '@/components/BrandLockup.vue'

const route = useRoute()
const token = computed(() => String(route.query.token || ''))
const decision = computed(() => String(route.query.decision || '').toUpperCase())
const loading = ref(true)
const offer = ref(null)
const error = ref('')

const accepted = computed(() => offer.value?.responseStatus === '已接受')
const finished = computed(() => ['已接受', '已拒绝'].includes(offer.value?.responseStatus))
const validDecision = computed(() => ['ACCEPTED', 'DECLINED'].includes(decision.value))

async function submitEmailDecision() {
  loading.value = true
  error.value = ''
  try {
    if (!token.value || !validDecision.value) throw new Error('邮件按钮链接不完整，请联系负责 HR')
    const current = await getPublicOffer(token.value)
    offer.value = current
    if (finished.value) return
    if (current.expired) throw new Error('这份 Offer 已过有效期，请联系负责 HR 重新确认')
    offer.value = await respondToPublicOffer(token.value, {
      decision: decision.value,
      reason: decision.value === 'DECLINED' ? '候选人通过邮件按钮拒绝 Offer' : ''
    })
  } catch (cause) {
    error.value = cause.response?.data?.message || cause.message || '回复提交失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

onMounted(submitEmailDecision)
</script>

<template>
  <main class="offer-action-page">
    <section class="offer-action-card">
      <BrandLockup />
      <div v-if="loading" class="action-state loading-state">
        <span class="spinner" aria-hidden="true"></span>
        <h1>正在提交您的选择…</h1>
        <p>请稍候，不需要再次点击。</p>
      </div>
      <div v-else-if="error" class="action-state error-state">
        <span class="state-icon" aria-hidden="true">!</span>
        <small>OFFER 回复</small>
        <h1>暂时无法提交</h1>
        <p>{{ error }}</p>
      </div>
      <div v-else class="action-state" :class="accepted ? 'accepted-state' : 'declined-state'">
        <span class="state-icon" aria-hidden="true">{{ accepted ? '✓' : '—' }}</span>
        <small>{{ offer?.company }} · {{ offer?.jobName }}</small>
        <h1>{{ accepted ? '您已接受这份 Offer' : '您已拒绝这份 Offer' }}</h1>
        <p>结果已立即送达负责 HR，无需登录，也不需要再进行其他操作。</p>
        <dl><div><dt>候选人</dt><dd>{{ offer?.personName }}</dd></div><div><dt>回复结果</dt><dd>{{ offer?.responseStatus }}</dd></div><div><dt>负责 HR</dt><dd>{{ offer?.owner }}</dd></div></dl>
      </div>
      <footer>SetHub 人才管理 · 此页面仅用于显示本次回复结果</footer>
    </section>
  </main>
</template>

<style scoped>
.offer-action-page{min-height:100vh;display:grid;place-items:center;box-sizing:border-box;padding:28px 16px;background:radial-gradient(circle at 75% 10%,#dfeaff 0,transparent 38%),linear-gradient(145deg,#eef4ff,#f8fafc);font-family:Inter,"Microsoft YaHei",sans-serif;color:#27364a}.offer-action-card{width:min(540px,100%);box-sizing:border-box;padding:30px;border:1px solid #dbe5f2;border-radius:24px;background:#fff;box-shadow:0 24px 70px rgba(45,73,112,.14)}.action-state{text-align:center;padding:42px 8px 30px}.action-state.loading-state{display:block}.state-icon,.spinner{display:grid;place-items:center;width:64px;height:64px;margin:0 auto 20px;border-radius:20px;font-size:30px;font-weight:800}.action-state small{color:#718096;font-weight:600}.action-state h1{margin:10px 0 12px;font-size:26px}.action-state p{margin:0;color:#64748b;line-height:1.8}.accepted-state .state-icon{background:#e8f8ee;color:#17834f}.declined-state .state-icon{background:#fff0f1;color:#c34456}.error-state .state-icon{background:#fff4e6;color:#b66b14}.loading-state .spinner{border:5px solid #e4ecf8;border-top-color:#3474ed;border-radius:50%;animation:spin .8s linear infinite}.action-state dl{overflow:hidden;margin:26px 0 0;border-radius:13px;background:#e7edf4;text-align:left}.action-state dl div{display:grid;grid-template-columns:110px 1fr;gap:12px;padding:12px 15px;background:#f8fafc;border-bottom:1px solid #e7edf4}.action-state dl div:last-child{border:0}.action-state dt{color:#8a99ad}.action-state dd{margin:0;font-weight:650}.offer-action-card footer{padding-top:20px;border-top:1px solid #edf1f6;text-align:center;color:#9aa7b8;font-size:11px}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:520px){.offer-action-card{padding:22px}.action-state{padding:34px 0 24px}.action-state dl div{grid-template-columns:90px 1fr}}
</style>
