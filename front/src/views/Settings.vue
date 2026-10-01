<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMailSettings, saveMailSettings, testMailSettings } from '@/api/candidate'
import { getCompanyList } from '@/api/company'
import { getEmployeeList, updateEmployeeAccount } from '@/api/employee'
import { currentRelease, loadReleases } from '@/data/releases'

const companies=ref([]),companyId=ref(''),employees=ref([])
const mail=reactive({companyId:'',provider:'QQ邮箱',host:'smtp.qq.com',port:465,username:'',senderEmail:'',password:'',ssl:true,starttls:false,passwordConfigured:false,configured:false})
const loading=ref(true),saving=ref(false),testing=ref(false),testRecipient=ref(''),mailError=ref(''),mailMessage=ref('')
const accountSaving=ref(null),accountError=ref('')
const providers={
  'QQ邮箱':{host:'smtp.qq.com',port:465,ssl:true,starttls:false},
  '腾讯企业邮箱':{host:'smtp.exmail.qq.com',port:465,ssl:true,starttls:false},
  '网易163邮箱':{host:'smtp.163.com',port:465,ssl:true,starttls:false},
  '阿里企业邮箱':{host:'smtp.qiye.aliyun.com',port:465,ssl:true,starttls:false},
  '自定义':null
}
function preset(){const value=providers[mail.provider];if(value)Object.assign(mail,value)}
async function loadMail(){
  if(!companyId.value)return
  loading.value=true;mailError.value='';mailMessage.value=''
  try{const data=await getMailSettings(companyId.value);Object.assign(mail,data,{password:'',companyId:companyId.value});testRecipient.value=data.senderEmail||data.username||''}
  catch(error){mailError.value=error.response?.data?.message||error.message||'发件配置读取失败'}
  finally{loading.value=false}
}
async function saveMail(test=false){
  saving.value=true;mailError.value='';mailMessage.value=''
  try{const data=await saveMailSettings({...mail,companyId:Number(companyId.value)});Object.assign(mail,data,{password:''});mailMessage.value='这家公司的发件邮箱已保存。';if(test)await sendTest()}
  catch(error){mailError.value=error.response?.data?.message||error.message||'发件配置保存失败'}
  finally{saving.value=false}
}
async function sendTest(){
  if(!testRecipient.value.trim()){mailError.value='请填写接收测试邮件的邮箱';return}
  testing.value=true;mailError.value='';mailMessage.value=''
  try{await testMailSettings(testRecipient.value.trim(),Number(companyId.value));mailMessage.value='测试邮件已发出，请到收件箱查看。'}
  catch(error){mailError.value=error.response?.data?.message||error.message||'测试邮件发送失败'}
  finally{testing.value=false}
}
async function saveAccount(employee){
  accountSaving.value=employee.id;accountError.value=''
  try{
    const updated=await updateEmployeeAccount(employee.id,{role:employee.role,title:employee.title,accountEnabled:employee.accountEnabled,username:employee.username,password:employee.newPassword||''})
    Object.assign(employee,updated,{newPassword:''})
    ElMessage.success('员工角色、职业和登录账号已保存')
  }catch(error){accountError.value=error.response?.data?.message||error.message||'账号保存失败'}
  finally{accountSaving.value=null}
}
onMounted(async()=>{
  loading.value=true
  try{
    const [companyRows,employeeRows]=await Promise.all([getCompanyList(),getEmployeeList(),loadReleases().catch(()=>[])])
    companies.value=companyRows;companyId.value=String(companyRows[0]?.id||'')
    employees.value=employeeRows.map(item=>({...item,accountEnabled:Boolean(item.accountActive),newPassword:''}))
    await loadMail()
  }catch(error){mailError.value=error.response?.data?.message||error.message||'系统设置读取失败';loading.value=false}
})
</script>

<template>
  <div class="page-heading"><div><div class="eyebrow">SYSTEM SETTINGS</div><h1>系统设置</h1><p>按公司管理发件邮箱，并分别管理员工的职业、系统角色和登录账号。</p></div></div>

  <section class="surface settings-panel">
    <div class="section-heading"><div><div class="eyebrow">COMPANY EMAIL</div><h2>公司发件邮箱</h2><p class="panel-caption">Offer 会根据岗位所属公司，自动使用对应公司的邮箱发送。</p></div><span class="status" :class="{ready:mail.configured}">{{mail.configured?'已配置':'待配置'}}</span></div>
    <label class="company-picker field"><span>选择公司 <b>必填</b></span><select v-model="companyId" @change="loadMail"><option v-for="company in companies" :key="company.id" :value="String(company.id)">{{company.name}}</option></select><small>每家公司保存一套独立配置，互不混用。</small></label>
    <div v-if="loading" class="loading-state"><span class="spinner"></span>正在读取配置…</div>
    <form v-else class="mail-form" @submit.prevent="saveMail(false)">
      <label class="field"><span>邮箱类型 <b>必填</b></span><select v-model="mail.provider" @change="preset"><option v-for="(_,name) in providers" :key="name">{{name}}</option></select></label>
      <label class="field"><span>发件邮箱 <b>必填</b></span><input v-model.trim="mail.username" type="email" placeholder="例如：hr@company.com" @blur="!mail.senderEmail&&(mail.senderEmail=mail.username)" /></label>
      <label class="field"><span>邮箱授权码 <b>必填</b></span><input v-model="mail.password" type="password" autocomplete="new-password" :placeholder="mail.passwordConfigured?'已保存；不修改请留空':'填写邮箱后台生成的授权码'" /><small>授权码是邮箱后台生成的“专用钥匙”，不是 QQ 登录密码。</small></label>
      <label class="field"><span>邮件显示的发件地址 <b>必填</b></span><input v-model.trim="mail.senderEmail" type="email" placeholder="必须与登录邮箱一致" /></label>
      <template v-if="mail.provider==='自定义'"><label class="field"><span>SMTP 发件服务器 <b>必填</b></span><input v-model.trim="mail.host" placeholder="smtp.example.com" /></label><label class="field"><span>端口 <b>必填</b></span><input v-model.number="mail.port" type="number" min="1" max="65535" /></label></template>
      <label class="field full"><span>接收测试邮件的邮箱</span><input v-model.trim="testRecipient" type="email" placeholder="先给自己发一封测试邮件" /></label>
      <p v-if="mailError" class="error full">{{mailError}}</p><p v-if="mailMessage" class="success full">{{mailMessage}}</p>
      <div class="actions full"><button class="btn" :disabled="saving||testing">{{saving?'正在保存…':'保存配置'}}</button><button type="button" class="btn btn-primary" :disabled="saving||testing" @click="saveMail(true)">{{testing?'正在发送…':'保存并测试发送'}}</button></div>
    </form>
  </section>

  <section class="surface settings-panel">
    <div class="section-heading"><div><div class="eyebrow">EMPLOYEE ACCOUNTS</div><h2>员工角色与登录账号</h2><p class="panel-caption">“职业”写摄影师、设计师等实际岗位；“系统角色”决定是否属于 HR。两项互相独立。</p></div></div>
    <p v-if="accountError" class="error">{{accountError}}</p>
    <div class="account-table-wrap"><table class="account-table"><thead><tr><th>员工</th><th>职业</th><th>系统角色</th><th>允许登录</th><th>登录账号</th><th>新密码</th><th></th></tr></thead><tbody>
      <tr v-for="employee in employees" :key="employee.id">
        <td><strong>{{employee.name}}</strong><small>{{employee.department||'部门待补充'}} · {{employee.employeeNumber||'暂无工号'}}</small></td>
        <td><input v-model.trim="employee.title" placeholder="例如：摄影师" /></td>
        <td><select v-model="employee.role"><option value="HR">HR</option><option value="EMPLOYEE">普通员工</option></select></td>
        <td><label class="switch"><input v-model="employee.accountEnabled" type="checkbox" /><span>{{employee.accountEnabled?'已开启':'未开启'}}</span></label></td>
        <td><input v-model.trim="employee.username" :disabled="!employee.accountEnabled" placeholder="登录账号" /></td>
        <td><input v-model="employee.newPassword" :disabled="!employee.accountEnabled" type="password" :placeholder="employee.hasAccount?'留空则不修改':'至少 6 位'" /></td>
        <td><button class="btn btn-small btn-primary" :disabled="accountSaving===employee.id" @click="saveAccount(employee)">{{accountSaving===employee.id?'保存中…':'保存'}}</button></td>
      </tr>
    </tbody></table></div>
  </section>

  <section class="surface settings-panel version-panel">
    <div class="version-mark">{{currentRelease.version}}</div>
    <div class="version-copy"><div class="eyebrow">PRODUCT VERSION</div><h2>当前版本 {{currentRelease.version}}</h2><p>{{currentRelease.summary}}</p><ul><li v-for="change in currentRelease.changes.slice(0,3)" :key="change">{{change}}</li></ul></div>
    <router-link class="btn btn-primary version-link" to="/updates">查看更新内容与历史版本 →</router-link>
  </section>
</template>

<style scoped>
.settings-panel{padding:26px;margin-bottom:20px;border:1px solid #c9d6e6}.section-heading{display:flex;justify-content:space-between;gap:18px;margin-bottom:22px}.status{height:max-content;padding:7px 12px;border-radius:999px;background:#fff1f2;color:#be123c;font-size:12px;font-weight:700}.status.ready{background:#ecfdf3;color:#15803d}.company-picker{max-width:520px;margin-bottom:22px}.mail-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.field{display:flex;flex-direction:column;gap:8px}.field>span{color:#243447;font-size:13px;font-weight:700}.field b{padding:2px 6px;border-radius:999px;background:#fff0ed;color:#c2412d;font-size:10px}.field input,.field select,.account-table input,.account-table select{box-sizing:border-box;width:100%;min-height:43px;border:1px solid #b9c8da;border-radius:10px;background:#fff;color:#172033;padding:9px 11px}.field small{color:#65758a}.full{grid-column:1/-1}.actions{display:flex;justify-content:flex-end;gap:10px;padding-top:18px;border-top:1px solid #dce4ee}.error,.success{padding:12px 14px;border-radius:10px;font-weight:650}.error{background:#fff1f2;color:#be123c}.success{background:#ecfdf3;color:#166534}.account-table-wrap{overflow:auto}.account-table{width:100%;min-width:1050px;border-collapse:collapse}.account-table th{text-align:left;padding:11px;color:#667085;font-size:11px;border-bottom:1px solid #dce4ee}.account-table td{padding:13px 9px;border-bottom:1px solid #e7edf4}.account-table td:first-child{min-width:160px}.account-table strong,.account-table small{display:block}.account-table strong{color:#172033}.account-table small{margin-top:5px;color:#667085;font-size:10px}.switch{display:flex;align-items:center;gap:7px;white-space:nowrap}.switch input{width:18px;min-height:18px}.switch span{font-size:11px}.version-panel{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:22px;background:linear-gradient(135deg,#fff,#eef4ff)}.version-mark{display:grid;place-items:center;width:66px;height:66px;border-radius:19px;background:#3370ff;color:#fff;font-size:21px;font-weight:850;box-shadow:0 10px 24px rgba(51,112,255,.22)}.version-copy h2{margin:5px 0 8px;color:#172033;font-size:18px}.version-copy p{margin:0;color:#475467;font-size:12px}.version-copy ul{display:flex;gap:7px 22px;flex-wrap:wrap;margin:13px 0 0;padding-left:18px;color:#526174}.version-copy li{font-size:10px}.version-link{white-space:nowrap}@media(max-width:900px){.version-panel{grid-template-columns:auto 1fr}.version-link{grid-column:1/-1;width:max-content}}@media(max-width:720px){.mail-form{grid-template-columns:1fr}.actions{flex-direction:column}.settings-panel{padding:18px}.version-panel{grid-template-columns:1fr}.version-mark{width:55px;height:55px}.version-link{grid-column:auto;width:100%}}
</style>
<style scoped>
.version-panel .version-link{color:#fff!important;font-weight:700}
</style>

