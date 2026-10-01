<template>
  <el-dialog v-model="visible" title="把每一次相遇，带进人才库" width="min(1080px, 95vw)" top="4vh" :close-on-click-modal="false" :close-on-press-escape="!importing" :show-close="!importing" :before-close="beforeClose" destroy-on-close>
    <p class="transfer-intro muted">上传 CSV → 对应字段 → 预览并确认 → 建立人才档案。原有档案不会被覆盖。</p>
    <div class="transfer-steps" aria-label="导入进度"><span :class="{ current: step === 'mapping' }">01 选择文件与字段</span><i aria-hidden="true">→</i><span :class="{ current: step === 'preview' }">02 校验与预演</span><i aria-hidden="true">→</i><span :class="{ current: step === 'results' }">03 导入结果</span></div>

    <template v-if="step === 'mapping'">
      <div class="upload-area"><div class="upload-symbol" aria-hidden="true">↑</div><strong>{{ fileName || '从 CSV 文件开始' }}</strong><p>{{ csvDocument ? `${csvDocument.rows.length} 行人才数据，${csvDocument.headers.length} 个字段` : 'UTF-8 编码，最多 1,000 条 / 5 MB，支持逗号、引号与跨行备注' }}</p><div class="upload-actions"><label class="btn btn-primary file-picker">{{ reading ? '读取中…' : (fileName ? '重新选择文件' : '选择 CSV 文件') }}<input type="file" accept=".csv,text/csv" :disabled="reading" aria-label="选择 CSV 文件" @change="readFile" /></label><button type="button" class="btn btn-secondary" @click="downloadImportTemplate">下载空白模板</button></div></div>
      <div v-if="csvDocument" class="mapping-area">
        <div class="mapping-heading"><h3>让字段一一对应</h3><span class="muted">未选择的字段会留空；来源与 HR 可使用下方默认值。</span></div>
        <div class="defaults-grid"><label class="field">文件来源留空时使用<select v-model="defaultSource"><option v-for="source in IMPORT_SOURCES" :key="source">{{ source }}</option></select></label><label class="field">文件 HR 留空时使用<HrPicker v-model="defaultOwner" :employees="hrs" placeholder="输入姓名模糊搜索 HR" /></label></div>
        <div class="mapping-grid"><label v-for="field in IMPORT_FIELDS" :key="field.key" class="field"><span>{{ field.label }}<small v-if="field.key === 'name'" class="required"> *</small></span><select v-model.number="mapping[field.key]"><option :value="-1">不导入此字段</option><option v-for="(header, index) in csvDocument.headers" :key="index" :value="index">{{ index + 1 }}. {{ header || '未命名列' }}</option></select><small class="sample-value" :title="sampleValue(field.key)">{{ sampleValue(field.key) }}</small></label></div>
        <p class="mapping-hint">姓名 / 昵称、来源、HR 和至少一种联系方式必填；薪资请填写 K / 月的数字。岗位名称须与岗位库唯一对应，留空可作为储备人才。状态、结果和日期会逐行校验。</p>
      </div>
    </template>

    <template v-else>
      <div class="preview-summary"><div><strong>{{ rows.length }}</strong><span>文件总行数</span></div><div><strong>{{ validRows.length }}</strong><span>校验通过</span></div><div><strong>{{ invalidRows.length }}</strong><span>需要修改</span></div><div><strong>{{ duplicateRows.length }}</strong><span>疑似重复</span></div><div v-if="step === 'results'"><strong>{{ successRows.length }}</strong><span>已成功导入</span></div></div>
      <div v-if="checkingDuplicates" class="progress-notice" role="status">正在核对现有档案：{{ checkedRows }} / {{ validRows.length }}，此过程不会写入数据。</div>
      <div v-if="importing" class="progress-notice" role="status"><span>正在导入 {{ currentImportIndex }} / {{ currentImportCount }}，请保留此窗口…</span><progress :value="currentImportIndex" :max="Math.max(currentImportCount, 1)"></progress></div>
      <div v-if="step === 'preview'" class="selection-toolbar"><label><input v-model="showOnlyProblems" type="checkbox" /> 只看异常与疑似重复</label><div><button type="button" class="text-button" :disabled="checkingDuplicates" @click="selectRows(false)">选择无重复的有效行</button><button type="button" class="text-button" :disabled="checkingDuplicates" @click="selectRows(true)">选择全部有效行（含疑似重复）</button><button type="button" class="text-button" :disabled="checkingDuplicates" @click="rows.forEach(row => row.selected = false)">清空选择</button></div></div>
      <div v-else class="result-banner" :class="{ 'has-failures': failedRows.length }"><strong>{{ importing ? '正在建立档案' : failedRows.length ? '部分行未导入，可以单独重试' : successRows.length ? '所选人才已导入' : '尚无人才导入成功' }}</strong><span>{{ successRows.length }} 行成功 · {{ failedRows.length }} 行失败 · {{ rows.filter(row => row.status === 'pending').length }} 行未导入</span><small>批次 {{ importBatch }} · 同批次行重试会复用已成功创建的档案。</small></div>
      <div class="preview-table-wrap"><table class="preview-table"><thead><tr><th v-if="step === 'preview'" scope="col">导入</th><th scope="col">原文件行</th><th scope="col">人才 / 联系方式</th><th scope="col">岗位 / 期望薪资</th><th scope="col">来源 / HR</th><th scope="col">校验与处理结果</th></tr></thead><tbody><tr v-for="row in pageRows" :key="row.sourceRow" :class="{ 'invalid-row': row.errors.length }"><td v-if="step === 'preview'"><input v-model="row.selected" type="checkbox" :disabled="row.errors.length > 0 || checkingDuplicates" :aria-label="`选择文件第 ${row.sourceRow} 行`" /></td><td>{{ row.sourceRow }}</td><td><strong>{{ row.payload.name || '缺少姓名' }}</strong><small>{{ [row.payload.phone, row.payload.wechat, row.payload.email].filter(Boolean).join(' · ') || '缺少联系方式' }}</small></td><td>{{ row.payload.job || '暂不关联岗位' }}<small>{{ salaryLabel(row.payload) }}</small></td><td>{{ row.payload.source || '未指定来源' }}<small>{{ row.payload.owner || '未指定 HR' }}</small></td><td class="checks-cell"><span v-if="row.status === 'success'" class="check-success">已导入 · <router-link :to="`/person/${row.savedId}`" target="_blank" rel="noopener">查看档案 ↗</router-link></span><span v-else-if="row.status === 'failed'" class="check-error">{{ row.resultMessage }}</span><template v-else><span v-if="!row.errors.length && !row.warnings.length && !row.duplicateMatches.length" class="check-success">{{ row.duplicateCheck === 'pending' ? '待重复检查' : '校验通过' }}</span><span v-for="message in row.errors" :key="message" class="check-error">{{ message }}</span><span v-for="message in row.warnings" :key="message" class="check-warning">{{ message }}</span><div v-if="row.duplicateMatches.length" class="check-warning">现有 {{ row.duplicateMatches.length }} 份疑似档案：<router-link v-for="match in row.duplicateMatches.slice(0, 3)" :key="match.id" :to="`/person/${match.id}`" target="_blank" rel="noopener">{{ match.name || '查看' }} ↗ </router-link></div></template></td></tr></tbody></table><p v-if="!displayRows.length" class="table-empty">没有符合当前条件的行。</p></div>
      <div class="preview-pagination"><span>共 {{ displayRows.length }} 行 · 每页 10 行</span><div><button type="button" aria-label="上一页" :disabled="previewPage === 1" @click="previewPage--">‹</button><span>{{ previewPage }} / {{ totalPages }}</span><button type="button" aria-label="下一页" :disabled="previewPage >= totalPages" @click="previewPage++">›</button></div></div>
      <label v-if="step === 'preview'" class="confirm-import"><input v-model="confirmed" type="checkbox" :disabled="checkingDuplicates" /><span>我已核对预演，确认新增选中的 <strong>{{ selectedRows.length }}</strong> 份人才档案。疑似重复项由我确认，不自动合并。</span></label>
      <p v-if="invalidRows.length && step === 'preview'" class="mapping-hint">有 {{ invalidRows.length }} 行未通过校验，不会导入。可返回调整字段对应关系，或修改原文件后重新选择。</p>
    </template>
    <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
    <template #footer><div class="transfer-footer"><span class="muted footer-hint">{{ step === 'mapping' ? '上传和预演不会修改人才库' : step === 'preview' ? '只有点击确认导入后才会写入' : '成功行不会在本批次重试时重复创建' }}</span><button type="button" class="btn btn-secondary" :disabled="importing" @click="visible = false">{{ step === 'results' ? '完成' : '取消' }}</button><button v-if="step === 'mapping'" type="button" class="btn btn-primary" :disabled="!csvDocument || reading || loadingPositions" @click="previewImport">{{ loadingPositions ? '加载岗位中…' : '校验并预演' }}</button><template v-else-if="step === 'preview'"><button type="button" class="btn btn-secondary" :disabled="checkingDuplicates" @click="step = 'mapping'; confirmed = false">返回调整</button><button type="button" class="btn btn-primary" :disabled="!confirmed || !selectedRows.length || checkingDuplicates" @click="runImport(false)">确认导入 {{ selectedRows.length }} 行</button></template><button v-else-if="failedRows.length" type="button" class="btn btn-primary" :disabled="importing" @click="runImport(true)">重试 {{ failedRows.length }} 个失败行</button></div></template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { createCandidate, getDuplicates } from '@/api/candidate'
import { getPositionList } from '@/api/position'
import { getHrList } from '@/api/hr'
import HrPicker from '@/components/HrPicker.vue'
import { IMPORT_FIELDS, IMPORT_SOURCES, parseCSVDocument, inferColumnMapping, prepareImportRows, downloadImportTemplate } from '@/utils/transfer'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'saved'])
const visible = computed({ get: () => props.modelValue, set: value => { if (!importing.value) emit('update:modelValue', value) } })
const step = ref('mapping'), fileName = ref(''), csvDocument = ref(null), reading = ref(false), error = ref('')
const mapping = reactive({}), defaultSource = ref('历史导入'), defaultOwner = ref('')
const positions = ref([]), loadingPositions = ref(false), positionsAvailable = ref(false)
const hrs = ref([])
const rows = ref([]), confirmed = ref(false), showOnlyProblems = ref(false), previewPage = ref(1)
const checkingDuplicates = ref(false), checkedRows = ref(0), importing = ref(false)
const currentImportIndex = ref(0), currentImportCount = ref(0), importBatch = ref('')
let checkGeneration = 0
const validRows = computed(() => rows.value.filter(row => !row.errors.length))
const invalidRows = computed(() => rows.value.filter(row => row.errors.length))
const isDuplicate = row => row.duplicateMatches.length || row.warnings.some(message => message.startsWith('与文件第'))
const duplicateRows = computed(() => rows.value.filter(isDuplicate))
const selectedRows = computed(() => rows.value.filter(row => row.selected && !row.errors.length && row.status !== 'success'))
const successRows = computed(() => rows.value.filter(row => row.status === 'success'))
const failedRows = computed(() => rows.value.filter(row => row.status === 'failed'))
const displayRows = computed(() => showOnlyProblems.value && step.value === 'preview' ? rows.value.filter(row => row.errors.length || row.warnings.length || row.duplicateMatches.length) : rows.value)
const totalPages = computed(() => Math.max(1, Math.ceil(displayRows.value.length / 10)))
const pageRows = computed(() => displayRows.value.slice((previewPage.value - 1) * 10, previewPage.value * 10))
watch(totalPages, value => { if (previewPage.value > value) previewPage.value = value })
watch(showOnlyProblems, () => { previewPage.value = 1 })
function beforeClose(done) { if (!importing.value) done() }
function newBatchId() { return globalThis.crypto?.randomUUID?.() || `import-${Date.now()}-${Math.random().toString(36).slice(2, 12)}` }
function unwrap(response) { let value = response; if (value?.data !== undefined) value = value.data; if (value?.data !== undefined) value = value.data; return value }
function salaryLabel(payload) { return payload.salaryMin != null && payload.salaryMax != null && Number.isFinite(payload.salaryMin) && Number.isFinite(payload.salaryMax) ? `${payload.salaryMin} — ${payload.salaryMax} K / 月` : '薪资未填写' }
function sampleValue(key) { if (mapping[key] < 0) return '留空'; return csvDocument.value?.rows[0]?.values[mapping[key]] || '首行为空' }

watch(() => props.modelValue, async open => {
  if (!open) { checkGeneration++; return }
  step.value = 'mapping'; fileName.value = ''; csvDocument.value = null; error.value = ''; rows.value = []; confirmed.value = false; showOnlyProblems.value = false; previewPage.value = 1; defaultOwner.value = ''; defaultSource.value = '历史导入'; importBatch.value = newBatchId()
  loadingPositions.value = true; positionsAvailable.value = false
  try { const [positionData, employeeData] = await Promise.all([getPositionList(), getHrList()]); const data = unwrap(positionData); positions.value = Array.isArray(data) ? data : data?.records || []; hrs.value = employeeData; defaultOwner.value = (hrs.value.find(hr => hr.current) || hrs.value[0])?.name || ''; positionsAvailable.value = true }
  catch (err) { error.value = `岗位库暂未加载：${err.message || '请检查服务连接'}。可暂不映射岗位字段。` }
  finally { loadingPositions.value = false }
})

async function readFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  reading.value = true; error.value = ''; csvDocument.value = null
  try {
    if (file.size > 5 * 1024 * 1024) throw new Error('文件超过 5 MB，请拆分后导入。')
    if (!file.name.toLowerCase().endsWith('.csv')) throw new Error('请选择 CSV 文件，Excel 文件请另存为 CSV UTF-8。')
    const text = await file.text()
    if (text.includes('\uFFFD')) throw new Error('文件编码无法识别，请在 Excel 中另存为“CSV UTF-8”后重试。')
    const document = parseCSVDocument(text)
    if (!document.rows.length) throw new Error('文件只有表头，请补充人才数据后重试。')
    if (document.rows.length > 1000) throw new Error('一次最多导入 1,000 条，请拆分为多个文件。')
    csvDocument.value = document; fileName.value = file.name
    Object.assign(mapping, inferColumnMapping(document.headers)); importBatch.value = newBatchId()
  } catch (err) { error.value = err.message; fileName.value = '' }
  finally { reading.value = false; event.target.value = '' }
}

async function previewImport() {
  error.value = ''
  if (!csvDocument.value) return
  if (mapping.name == null || mapping.name < 0) { error.value = '请将“姓名 / 昵称”对应到文件中的姓名列。'; return }
  if (['phone', 'wechat', 'email'].every(key => mapping[key] == null || mapping[key] < 0)) { error.value = '请至少对应手机、微信、邮箱中的一个联系字段。'; return }
  if (mapping.job >= 0 && !positionsAvailable.value) { error.value = '岗位库尚未加载，无法核对关联。请重新打开导入窗口，或暂不映射岗位字段。'; return }
  rows.value = prepareImportRows(csvDocument.value, mapping, { positions: positions.value, defaultSource: defaultSource.value, defaultOwner: defaultOwner.value, importBatch: importBatch.value }).map(row => ({ ...row, selected: !row.errors.length }))
  step.value = 'preview'; previewPage.value = 1; confirmed.value = false; checkedRows.value = 0
  const generation = ++checkGeneration
  checkingDuplicates.value = true
  let nextIndex = 0
  const targets = validRows.value
  const worker = async () => {
    while (nextIndex < targets.length && generation === checkGeneration) {
      const row = targets[nextIndex++]
      try {
        const data = unwrap(await getDuplicates({ name: row.payload.name, phone: row.payload.phone, wechat: row.payload.wechat, email: row.payload.email }))
        if (generation !== checkGeneration) return
        row.duplicateMatches = Array.isArray(data) ? data : data?.records || data?.matches || []
        row.duplicateCheck = 'complete'
      } catch {
        if (generation !== checkGeneration) return
        row.duplicateCheck = 'failed'; row.warnings.push('现有档案重复检查暂不可用，请核对后决定是否导入')
      }
      row.selected = !isDuplicate(row) && row.duplicateCheck !== 'failed'
      checkedRows.value++
    }
  }
  try { await Promise.all(Array.from({ length: Math.min(4, targets.length) }, worker)) }
  finally { if (generation === checkGeneration) checkingDuplicates.value = false }
}

function selectRows(includeDuplicates) { rows.value.forEach(row => { row.selected = !row.errors.length && (includeDuplicates || (!isDuplicate(row) && row.duplicateCheck !== 'failed')) }); confirmed.value = false }
async function runImport(retry) {
  if (importing.value || (!retry && (!confirmed.value || checkingDuplicates.value))) return
  const targets = retry ? [...failedRows.value] : [...selectedRows.value]
  if (!targets.length) return
  importing.value = true; step.value = 'results'; error.value = ''; showOnlyProblems.value = false; previewPage.value = 1; currentImportIndex.value = 0; currentImportCount.value = targets.length
  let newlySaved = 0
  try {
    for (const row of targets) {
      if (row.status === 'success') continue
      try {
        const saved = unwrap(await createCandidate(row.payload))
        row.status = 'success'; row.savedId = saved.id || saved.person?.id; row.resultMessage = '已导入'; newlySaved++
      } catch (err) { row.status = 'failed'; row.resultMessage = err.response?.data?.message || err.message || '导入失败，可重试' }
      currentImportIndex.value++
    }
  } finally { importing.value = false; if (newlySaved) emit('saved', { importBatch: importBatch.value, count: newlySaved }) }
}
</script>

<style scoped>
.transfer-intro{font-size:12px;line-height:1.8;margin:0 0 22px}.transfer-steps{display:flex;align-items:center;gap:14px;margin-bottom:24px;padding:13px 16px;border-radius:13px;background:#f1f4f8;font-size:12px;color:#a0acba}.transfer-steps span.current{color:#607d9c;font-weight:600}.transfer-steps i{font-style:normal;color:#b9c3cf}.upload-area{border:1px dashed #c7d4e2;border-radius:18px;padding:30px;text-align:center;background:linear-gradient(145deg,#f4f7fb,#fafbfd)}.upload-symbol{display:grid;place-items:center;margin:0 auto 13px;width:39px;height:39px;border-radius:13px;background:#e8eff7;color:#8ca5bf;font-size:23px}.upload-area strong{font-size:15px;color:#5f7691;font-weight:550}.upload-area p{font-size:11px;color:#98a7b8;margin:10px 0 19px;line-height:1.8}.upload-actions{display:flex;justify-content:center;gap:10px}.btn{font-size:12px;padding:10px 16px;cursor:pointer}.btn:disabled{opacity:.5;cursor:not-allowed}.file-picker{position:relative;overflow:hidden}.file-picker input{position:absolute;inset:0;opacity:0;width:100%;height:100%;cursor:pointer}.file-picker:focus-within{outline:2px solid #8fa8c2;outline-offset:2px}.mapping-area{margin-top:27px}.mapping-heading{display:flex;justify-content:space-between;gap:15px;align-items:center;margin-bottom:18px}.mapping-heading h3{margin:0;font-size:14px;color:#60758e}.mapping-heading>span{font-size:10px}.defaults-grid{display:grid;grid-template-columns:1fr 1fr;gap:17px;padding:16px;background:#f4f7fa;border:1px solid #e5ebf2;border-radius:13px;margin-bottom:22px}.mapping-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px 19px}.field{display:flex;flex-direction:column;gap:7px;color:#697b91;font-size:11px}.field select,.field input{width:100%;min-width:0;box-sizing:border-box;font:inherit;font-size:12px;padding:10px 11px;color:#526b88;background:#f8fafc;border:1px solid #dfe7ef;border-radius:10px;outline:0}.field select:focus,.field input:focus{border-color:#9bb1c9;box-shadow:0 0 0 3px #eef2f7}.sample-value{display:block;color:#a1adbc;font-size:10px;line-height:1.5;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%;height:15px}.required{color:#a58173}.mapping-hint{font-size:11px;color:#98a6b6;line-height:1.9;margin:19px 0 0}.preview-summary{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:13px;margin-bottom:20px}.preview-summary>div{border:1px solid #e7edf3;background:#f8fafc;border-radius:12px;padding:14px 17px}.preview-summary strong{font-size:26px;font-weight:550;color:#6a809b;display:block;font-variant-numeric:tabular-nums}.preview-summary span{font-size:10px;color:#97a6b8;display:block;margin-top:7px}.progress-notice{display:flex;align-items:center;gap:15px;padding:12px 15px;border-radius:11px;background:#edf2f8;color:#7891ad;font-size:12px;margin-bottom:14px}.progress-notice progress{margin-left:auto;width:150px;accent-color:#98afc7}.selection-toolbar{display:flex;justify-content:space-between;gap:12px;align-items:center;margin:14px 0;font-size:11px;color:#7e91a8}.selection-toolbar>label{display:flex;align-items:center;gap:5px;white-space:nowrap}.selection-toolbar>div{display:flex;flex-wrap:wrap;gap:10px}.text-button{border:0;background:none;color:#859bb4;padding:0;font:inherit;font-size:10px;cursor:pointer}.text-button:hover{color:#516e8d}.text-button:disabled{opacity:.5;cursor:default}.preview-table-wrap{width:100%;overflow-x:auto;border:1px solid #e4ebf3;border-radius:13px}.preview-table{width:100%;border-collapse:collapse;text-align:left;font-size:11px;min-width:730px}.preview-table th{font-size:10px;font-weight:500;color:#9aaabd;background:#f4f7fb;padding:13px 12px;white-space:nowrap}.preview-table td{padding:14px 12px;border-top:1px solid #edf1f5;vertical-align:top;color:#7e91a7;line-height:1.6}.preview-table strong{font-weight:550;color:#5e7692}.preview-table small{display:block;font-size:10px;color:#a0adbd;margin-top:4px;max-width:200px;word-break:break-word}.preview-table .checks-cell{min-width:180px;max-width:280px;font-size:10px}.checks-cell span{display:block;line-height:1.8}.check-success{color:#8da699}.check-warning{color:#ac9a73}.check-error{color:#b18c7e}.checks-cell a{color:inherit;text-decoration:underline;text-underline-offset:3px}.invalid-row{background:#fbf8f6}.preview-pagination{display:flex;justify-content:space-between;align-items:center;font-size:10px;color:#9aaabd;margin-top:14px}.preview-pagination>div{display:flex;align-items:center;gap:13px}.preview-pagination button{border:1px solid #e0e7ef;border-radius:7px;background:#f8fafc;width:26px;height:26px;font-size:17px;color:#7b93af;cursor:pointer}.preview-pagination button:disabled{opacity:.35;cursor:default}.confirm-import{display:flex;align-items:flex-start;gap:8px;margin-top:23px;padding:15px;border:1px solid #dee8f2;border-radius:12px;background:#f3f7fb;font-size:11px;color:#7e95ad;line-height:1.8}.confirm-import input{margin-top:3px}.confirm-import strong{color:#5d7897;font-weight:600}input[type=checkbox]{accent-color:#8fa7c0;cursor:pointer}.inline-error{padding:12px 15px;margin:18px 0 0;font-size:12px;line-height:1.8;background:#f8efeb;color:#a68272;border:1px solid #ebdcd5;border-radius:12px}.transfer-footer{display:flex;align-items:center;gap:9px;justify-content:flex-end;border-top:1px solid #ecf0f5;padding-top:17px}.footer-hint{margin-right:auto;font-size:10px}.result-banner{border:1px solid #dce7e1;border-radius:13px;background:#f2f7f4;padding:16px 19px;display:flex;flex-direction:column;gap:8px;margin-bottom:19px;color:#859d90}.result-banner strong{font-weight:550;font-size:13px}.result-banner span{font-size:11px}.result-banner small{font-size:10px;word-break:break-all;color:#9cabaa}.result-banner.has-failures{background:#f8f5ef;border-color:#e6dfcf;color:#a59674}.table-empty{text-align:center;padding:25px;color:#9cabbc;font-size:12px}@media(max-width:750px){.mapping-grid{grid-template-columns:1fr 1fr}.mapping-heading{align-items:flex-start;flex-direction:column;gap:7px}.preview-summary{grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.preview-summary>div{padding:11px}.preview-summary strong{font-size:22px}.preview-summary span{font-size:9px}.selection-toolbar{align-items:flex-start;flex-direction:column;gap:11px}.transfer-steps{gap:7px;font-size:9px;padding:12px 9px;justify-content:space-between}.footer-hint{display:none}.transfer-footer{flex-wrap:wrap}.transfer-footer .btn{font-size:11px;padding:10px 12px}.upload-area{padding:25px 15px}.progress-notice{font-size:10px}.progress-notice progress{width:90px}}@media(max-width:450px){.mapping-grid,.defaults-grid{grid-template-columns:1fr}.upload-actions .btn{font-size:10px;padding:9px 13px}.transfer-intro{font-size:11px}}
</style>
