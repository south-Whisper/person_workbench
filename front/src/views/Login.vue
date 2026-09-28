<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getSetupStatus, loginApi, setupApi } from '@/api/user'
const router = useRouter(), route = useRoute()
const needsSetup = ref(false), loading = ref(false), checking = ref(true), error = ref('')
const form = reactive({ username: localStorage.getItem('rememberedUsername') || '', password: '', confirmPassword: '', remember: !!localStorage.getItem('rememberedUsername') })
async function check() {
  checking.value = true; error.value = ''
  try { needsSetup.value = !!(await getSetupStatus()).needsSetup }
  catch (e) { error.value = e.message } finally { checking.value = false }
}
onMounted(check)
async function submit() {
  if (loading.value || checking.value) return
  error.value = ''
  if (!form.username.trim() || !form.password) { error.value = '请输入账号和密码'; return }
  if (needsSetup.value && (form.password.length < 8 || form.password !== form.confirmPassword)) { error.value = '密码至少 8 位，且两次输入须一致'; return }
  loading.value = true
  try {
    const res = await (needsSetup.value ? setupApi : loginApi)({ username: form.username.trim(), password: form.password })
    if (!res.token) throw new Error('登录响应缺少凭证，请联系管理员')
    for (const key of ['token', 'username', 'role']) localStorage.setItem(key, res[key] || '')
    if (form.remember) localStorage.setItem('rememberedUsername', form.username.trim())
    else localStorage.removeItem('rememberedUsername')
    const redirect = String(route.query.redirect || '')
    router.replace(redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.startsWith('/login') ? redirect : '/dashboard')
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
</script>
<template>
  <div class="login-page">
    <div class="login-orb orb-one"></div><div class="login-orb orb-two"></div><div class="login-orb orb-three"></div>
    <div class="login-container">
      <section class="login-brand"><div class="login-wordmark">人才<span>招聘</span><i></i></div><div class="eyebrow">A SETHUB RECRUITING PRODUCT</div><h1>每一次相遇，<br>都有新的可能。</h1><p>连接招聘、成长与合作。<br>在同一份档案里，珍藏人才的每一段旅程。</p><div class="login-brand-footer"><span></span>SetHub 旗下人才招聘系统</div></section>
      <section class="login-box surface"><div class="login-emblem">招</div><h2>{{ needsSetup ? '建立人才招聘工作空间' : '欢迎回来' }}</h2><p class="login-subtitle">{{ needsSetup ? '设置第一个管理员账号，开始连接人才。' : '登录人才招聘，继续你的连接。' }}</p>
        <div v-if="checking" class="loading-state"><span class="spinner"></span>正在连接工作空间</div>
        <form v-else @submit.prevent="submit">
          <div v-if="error" class="login-error" role="alert">{{ error }} <button v-if="error.includes('服务')" type="button" class="text-link" @click="check">重试</button></div>
          <label class="field">账号<input v-model="form.username" autocomplete="username" placeholder="请输入账号" maxlength="50" required /></label>
          <label class="field">密码<input v-model="form.password" :autocomplete="needsSetup ? 'new-password' : 'current-password'" type="password" :placeholder="needsSetup ? '至少 8 位密码' : '请输入密码'" required /></label>
          <label v-if="needsSetup" class="field">确认密码<input v-model="form.confirmPassword" autocomplete="new-password" type="password" placeholder="再次输入密码" required /></label>
          <label class="remember-check"><input type="checkbox" v-model="form.remember" />记住账号</label>
          <button class="btn btn-primary login-submit" :disabled="loading">{{ loading ? '正在进入…' : needsSetup ? '创建工作空间' : '登录工作空间' }}<span>→</span></button>
          <p class="login-footnote">{{ needsSetup ? '该步骤仅在工作空间尚无账号时开放。' : '忘记密码请联系工作空间管理员。' }}</p>
        </form>
      </section>
    </div><div class="login-bottom">人才招聘 · SetHub 旗下产品</div>
  </div>
</template>
<style scoped>
.login-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#fafbfc,#f0f3f7);position:relative;overflow:hidden;isolation:isolate;padding:60px 30px}.login-orb{position:absolute;z-index:-1;border-radius:50%;filter:blur(75px);opacity:.55;animation:drift 20s ease-in-out infinite alternate}.orb-one{width:520px;height:520px;background:#d4e0eb;left:-180px;top:-130px}.orb-two{width:450px;height:450px;background:#dce3ea;right:-140px;bottom:-90px;animation-delay:-8s}.orb-three{width:330px;height:330px;background:#e1e7df;top:40%;left:38%;opacity:.3;animation-delay:-15s}@keyframes drift{to{transform:translate(80px,45px) scale(1.1)}}.login-container{width:100%;max-width:980px;display:grid;grid-template-columns:1fr 400px;gap:100px;align-items:center}.login-wordmark{font-size:35px;letter-spacing:-1.8px;font-weight:750;color:#566f85;margin-bottom:55px;display:flex;align-items:baseline}.login-wordmark span{font-weight:350}.login-wordmark i{height:5px;width:5px;background:#8fa395;margin-left:6px;border-radius:50%}.login-brand .eyebrow{font-size:9px;letter-spacing:2px;margin-bottom:20px}.login-brand h1{font-size:43px;line-height:1.4;letter-spacing:-1.5px;color:#3e505f;font-weight:550;margin:0 0 25px}.login-brand p{font-size:14px;line-height:2;color:#99a4af}.login-brand-footer{margin-top:50px;font-size:11px;color:#99a5b0;display:flex;align-items:center;gap:10px}.login-brand-footer span{width:22px;height:1px;background:#b4c2ce}.login-box{padding:38px;border-radius:26px;box-shadow:0 25px 70px #425b7210;background:#ffffffb5}.login-emblem{background:linear-gradient(140deg,#99adbd,#667f92);color:white;font-size:29px;font-family:Georgia;font-style:italic;width:40px;height:40px;display:grid;place-items:center;border-radius:12px;margin-bottom:26px}.login-box h2{font-size:25px;font-weight:600;color:#435766;margin:0 0 10px}.login-subtitle{font-size:12px;color:#9da6ae;margin-bottom:30px}.login-box .field{font-size:11px;gap:8px;margin-bottom:19px}.login-box .field input{font-size:12px;min-height:45px;background:#fafbfcaa}.remember-check{display:flex;align-items:center;gap:5px;color:#939fa9;font-size:11px;margin:3px 0 22px}.remember-check input{accent-color:#738b9d}.login-submit{width:100%;height:46px;justify-content:space-between;padding-inline:18px;font-size:12px}.login-footnote{font-size:10px;color:#b0b7be;text-align:center;margin:22px 0 0}.login-error{font-size:12px;background:#f8eeec;color:#a2756c;padding:12px;border-radius:8px;margin-bottom:18px}.login-bottom{position:absolute;bottom:25px;color:#acb5be;font-size:10px;letter-spacing:1px}@media(max-width:850px){.login-container{gap:35px;grid-template-columns:1fr 350px}.login-brand h1{font-size:34px}.login-box{padding:28px}}@media(max-width:650px){.login-container{display:block;max-width:380px}.login-brand{margin-bottom:25px}.login-brand h1,.login-brand p,.login-brand-footer,.login-brand .eyebrow{display:none}.login-wordmark{margin-bottom:20px;font-size:30px}.login-page{padding:35px 22px 65px}}
.login-emblem{font-size:20px;font-family:inherit;font-style:normal;font-weight:650}
.login-page{background:linear-gradient(135deg,#f8faff,#edf3ff)}.orb-one{background:#cfe0ff}.orb-two{background:#dbe7ff}.orb-three{background:#e5ecff}.login-wordmark{color:#245bdb}.login-wordmark i{background:#34c724}.login-emblem{background:linear-gradient(140deg,#5b8ff9,#3370ff);box-shadow:0 6px 16px rgba(51,112,255,.2)}.login-box h2{color:#1f2329}.login-brand h1{color:#1f2d3d}.remember-check input{accent-color:#3370ff}
</style>
