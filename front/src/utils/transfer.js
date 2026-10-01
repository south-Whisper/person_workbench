export const IMPORT_FIELDS = [
  { key: 'name', label: '姓名 / 昵称', aliases: ['姓名', '人才姓名', '候选人', '昵称', 'name'] },
  { key: 'phone', label: '手机', aliases: ['手机', '手机号', '手机号码', '电话', 'phone', 'mobile'] },
  { key: 'wechat', label: '微信', aliases: ['微信', '微信号', 'wechat'] },
  { key: 'email', label: '邮箱', aliases: ['邮箱', '电子邮箱', 'email'] },
  { key: 'source', label: '人才来源', aliases: ['人才来源', '招聘来源', '来源', 'source'] },
  { key: 'owner', label: 'HR', aliases: ['HR', '负责人', '跟进人', '招聘负责人', 'owner'] },
  { key: 'job', label: '关联岗位名称', aliases: ['关联岗位名称', '意向岗位', '应聘岗位', '岗位', '职位', 'job'] },
  { key: 'gender', label: '性别', aliases: ['性别', 'gender'] },
  { key: 'salaryMin', label: '期望月薪最低值（K）', aliases: ['期望月薪最低值（K）', '最低薪资', '薪资最小值', 'salaryMin'] },
  { key: 'salaryMax', label: '期望月薪最高值（K）', aliases: ['期望月薪最高值（K）', '最高薪资', '薪资最大值', 'salaryMax'] },
  { key: 'applyTime', label: '首次接触日期', aliases: ['首次接触日期', '首次联系时间', '应聘时间', '时间', 'applyTime'] },
  { key: 'status', label: '招聘状态', aliases: ['招聘状态', '状态', 'status'] },
  { key: 'result', label: '当前结果', aliases: ['当前结果', '招聘结果', '结果', 'result'] },
  { key: 'reason', label: '结果原因', aliases: ['结果原因', '未入职原因', '关闭原因', '原因', 'reason'] },
  { key: 'location', label: '所在城市', aliases: ['所在城市', '所在地', '城市', 'location'] },
  { key: 'company', label: '当前公司', aliases: ['当前公司', '公司', 'company'] },
  { key: 'currentRole', label: '当前职位', aliases: ['当前职位', '现任职位', 'currentRole'] },
  { key: 'tags', label: '人才标签', aliases: ['人才标签', '标签', 'tags'] },
  { key: 'experience', label: '工作经验', aliases: ['工作经验', '经验', 'experience'] },
  { key: 'companyIntent', label: '公司意愿', aliases: ['公司意愿', 'companyIntent'] },
  { key: 'talentIntent', label: '人才意愿', aliases: ['人才意愿', '候选人意愿', 'talentIntent'] },
  { key: 'remark', label: '人才备注', aliases: ['人才备注', '备注', 'remark'] },
  { key: 'nextStep', label: '下一步', aliases: ['下一步', '跟进计划', 'nextStep'] },
  { key: 'nextContactAt', label: '下次跟进时间', aliases: ['下次跟进时间', '跟进时间', 'nextContactAt'] }
]
export const IMPORT_SOURCES = ['BOSS直聘', '猎聘', '智联招聘', '内推', '历史导入', '其他']
export const IMPORT_STATUSES = ['待联系', '沟通中', '面试中', 'Offer中', '已入职', '人才储备', '已关闭']
export const IMPORT_RESULTS = ['待定', '录用', '候选人拒绝', '公司淘汰', '暂缓']

// Parse CSV as a state machine: quoted commas, escaped quotes, and multiline cells
// retain their contents. Physical source line numbers survive blank/multiline rows.
export function parseCSVDocument(input) {
  const text = String(input ?? '').replace(/^\uFEFF/, '')
  if (!text.trim()) throw new Error('文件为空，请选择包含表头和人才数据的 CSV 文件。')
  const records = []
  let cells = [], cell = '', quoted = false, closedQuote = false, line = 1, rowLine = 1
  const appendCell = () => { cells.push(cell); cell = ''; closedQuote = false }
  const appendRow = () => {
    appendCell()
    if (cells.some(value => value.trim() !== '')) records.push({ values: cells, sourceRow: rowLine })
    cells = []
  }
  for (let index = 0; index < text.length; index++) {
    const char = text[index]
    if (quoted) {
      if (char === '"') {
        if (text[index + 1] === '"') { cell += '"'; index++ }
        else { quoted = false; closedQuote = true }
      } else {
        cell += char
        if (char === '\n' || (char === '\r' && text[index + 1] !== '\n')) line++
      }
      continue
    }
    if (char === ',') { appendCell(); continue }
    if (char === '\n' || char === '\r') {
      appendRow()
      if (char === '\r' && text[index + 1] === '\n') index++
      line++; rowLine = line
      continue
    }
    if (closedQuote) {
      if (char === ' ' || char === '\t') continue
      throw new Error(`第 ${line} 行：结束引号后只能接逗号或换行。`)
    }
    if (char === '"') {
      if (cell.length) throw new Error(`第 ${line} 行：字段中的引号需要双写，并用引号包住整个字段。`)
      quoted = true
    } else cell += char
  }
  if (quoted) throw new Error(`第 ${rowLine} 行：引号没有闭合，请检查跨行内容。`)
  if (cell.length || cells.length || closedQuote) appendRow()
  if (!records.length) throw new Error('CSV 文件没有有效内容。')
  const header = records.shift()
  return { headers: header.values.map(value => value.trim()), headerLine: header.sourceRow, rows: records }
}

export function parseCSV(input) {
  const document = parseCSVDocument(input)
  return [document.headers, ...document.rows.map(row => row.values)]
}

const normalizedHeader = value => String(value).trim().toLowerCase().replace(/[\s_\-（）()]/g, '')
export function inferColumnMapping(headers) {
  const normalized = headers.map(normalizedHeader)
  return Object.fromEntries(IMPORT_FIELDS.map(field => [field.key, normalized.findIndex(header => field.aliases.some(alias => header === normalizedHeader(alias) || header.startsWith(normalizedHeader(alias) + '*必填') || header.startsWith(normalizedHeader(alias) + '至少填一项')))]))
}

function validDate(value, includeTime = false) {
  const match = String(value).match(includeTime ? /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T ](\d{1,2}):(\d{2})(?::(\d{2}))?)?$/ : /^(\d{4})-(\d{1,2})-(\d{1,2})$/)
  if (!match) return null
  const [, year, month, day, hours = '0', minutes = '0', seconds = '0'] = match
  const date = new Date(0)
  date.setFullYear(Number(year), Number(month) - 1, Number(day)); date.setHours(Number(hours), Number(minutes), Number(seconds), 0)
  if (date.getFullYear() !== Number(year) || date.getMonth() !== Number(month) - 1 || date.getDate() !== Number(day) || date.getHours() !== Number(hours) || date.getMinutes() !== Number(minutes) || date.getSeconds() !== Number(seconds)) return null
  const dateText = `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`
  return includeTime ? `${dateText}T${hours.padStart(2, '0')}:${minutes}:${seconds.padStart(2, '0')}` : dateText
}

export function prepareImportRows(document, mapping, { positions = [], defaultSource = '历史导入', defaultOwner = '', importBatch = '' } = {}) {
  const contactIndex = new Map()
  return document.rows.map(({ values, sourceRow }) => {
    const payload = {}
    const errors = [], warnings = []
    for (const field of IMPORT_FIELDS) payload[field.key] = mapping[field.key] >= 0 ? String(values[mapping[field.key]] ?? '').trim() : ''
    payload.source ||= defaultSource.trim()
    payload.owner ||= defaultOwner.trim()
    payload.status ||= '待联系'; payload.result ||= '待定'
    payload.companyIntent ||= '未判断'; payload.talentIntent ||= '未知'
    if (!['未判断', '低', '一般', '较高', '很高', '放弃', '有兴趣', '积极'].includes(payload.companyIntent)) errors.push(`公司意愿“${payload.companyIntent}”无效`)
    if (!['未知', '未判断', '明确拒绝', '暂不考虑', '可以了解', '有兴趣', '积极', '强烈'].includes(payload.talentIntent)) errors.push(`人才意愿“${payload.talentIntent}”无效`)
    if (!payload.name) errors.push('缺少姓名或昵称')
    if (!payload.owner) errors.push('缺少 HR')
    if (!payload.source) errors.push('缺少人才来源')
    else if (!IMPORT_SOURCES.includes(payload.source)) warnings.push(`来源“${payload.source}”将按原文保留`)
    if (![payload.phone, payload.wechat, payload.email].some(Boolean)) errors.push('手机、微信、邮箱至少填写一种')
    if (payload.phone && !/^(?:\+?86)?1[3-9]\d{9}$/.test(payload.phone.replace(/[\s()-]/g, ''))) errors.push('手机格式不正确，应为 11 位中国大陆手机号')
    if (payload.wechat && !/^[A-Za-z][-_A-Za-z0-9]{5,19}$/.test(payload.wechat)) errors.push('微信号格式不正确，应以字母开头并为 6—20 位')
    if (payload.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) errors.push('邮箱格式不正确')
    if (payload.gender && !['男', '女'].includes(payload.gender)) errors.push('性别只能填写男或女，未知可留空')
    if (!IMPORT_STATUSES.includes(payload.status)) errors.push(`招聘状态“${payload.status}”无效`)
    if (!IMPORT_RESULTS.includes(payload.result)) errors.push(`当前结果“${payload.result}”无效`)
    if ((['候选人拒绝', '公司淘汰'].includes(payload.result) || payload.status === '已关闭') && !payload.reason) errors.push('拒绝、淘汰或关闭需要填写原因')
    if (values.length > document.headers.length) errors.push('本行列数超过表头，检查逗号是否放在引号内')
    if (!!payload.salaryMin !== !!payload.salaryMax) errors.push('最低和最高薪资需要成对填写')
    if (payload.salaryMin && payload.salaryMax) {
      const numeric = /^\d+(?:\.\d+)?$/
      if (!numeric.test(payload.salaryMin) || !numeric.test(payload.salaryMax)) errors.push('薪资必须为非负数字，单位 K / 月')
      else if (Number(payload.salaryMin) >= Number(payload.salaryMax)) errors.push('最高薪资必须大于最低薪资')
      else if (Number(payload.salaryMax) > 100000) errors.push('薪资数值过大，请核对单位为 K / 月')
    }
    payload.salaryMin = payload.salaryMin === '' ? null : Number(payload.salaryMin)
    payload.salaryMax = payload.salaryMax === '' ? null : Number(payload.salaryMax)
    if (payload.applyTime) { const date = validDate(payload.applyTime.replaceAll('/', '-')); if (!date) errors.push('首次接触日期需为有效日期，如 2021-08-20'); else payload.applyTime = date }
    if (payload.nextContactAt) { const date = validDate(payload.nextContactAt.replaceAll('/', '-'), true); if (!date) errors.push('跟进时间需为有效日期时间，如 2026-10-01 14:30'); else payload.nextContactAt = date; if (!payload.nextStep) errors.push('设置跟进时间时需要填写下一步') } else payload.nextContactAt = null
    payload.jobId = null
    if (payload.job) {
      const matches = positions.filter(job => String(job.name).trim() === payload.job)
      if (!matches.length) errors.push(`岗位“${payload.job}”不存在，请先在岗位管理创建`)
      else if (matches.length > 1) errors.push(`岗位“${payload.job}”重名，请先消除名称歧义`)
      else { payload.jobId = matches[0].id; if (['暂停招聘', '已关闭', '已停招'].includes(matches[0].status)) warnings.push('关联的岗位目前已停招') }
    }
    for (const key of ['phone', 'wechat', 'email']) {
      if (!payload[key]) continue
      const contact = `${key}:${payload[key].toLowerCase()}`
      if (contactIndex.has(contact)) warnings.push(`与文件第 ${contactIndex.get(contact)} 行${{ phone: '手机', wechat: '微信', email: '邮箱' }[key]}相同`)
      else contactIndex.set(contact, sourceRow)
    }
    const limits = { name: 100, phone: 40, wechat: 100, email: 200, owner: 100, source: 100, location: 100, company: 200, currentRole: 200, tags: 500, experience: 100, remark: 5000, nextStep: 500, reason: 3000 }
    for (const [key, limit] of Object.entries(limits)) if ((payload[key]?.length || 0) > limit) errors.push(`${IMPORT_FIELDS.find(field => field.key === key)?.label || key}超过 ${limit} 字`)
    payload.importBatch = importBatch; payload.importRow = sourceRow
    return { sourceRow, payload, errors, warnings, duplicateMatches: [], duplicateCheck: 'pending', status: 'pending', resultMessage: '', savedId: null }
  })
}

export function escapeCSVCell(value) {
  let text = value == null ? '' : Array.isArray(value) ? value.join('，') : String(value)
  // Spreadsheet programs interpret formula prefixes even inside quoted CSV cells.
  if (/^[\t\r\n]/.test(text) || /^\s*[=+\-@]/.test(text)) text = `'${text}`
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}
export function stringifyCSV(rows) { return '\uFEFF' + rows.map(row => row.map(escapeCSVCell).join(',')).join('\r\n') + '\r\n' }
export function downloadCSV(content, filename) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = filename; anchor.style.display = 'none'
  document.body.appendChild(anchor); anchor.click(); anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function buildCandidateExport(rows) {
  const fields = IMPORT_FIELDS.filter(field => !['phone', 'wechat', 'email', 'salaryMin', 'salaryMax'].includes(field.key))
  return stringifyCSV([['人才编号', ...fields.map(field => field.label)], ...rows.map(person => [person.id, ...fields.map(field => person[field.key])])])
}
export function exportCandidatesCSV(rows, filename = '人才管理-人才摘要.csv') { downloadCSV(buildCandidateExport(rows), filename) }
export function downloadImportTemplate() {
  const keys = ['name', 'phone', 'wechat', 'email', 'source', 'owner', 'job', 'gender', 'salaryMin', 'salaryMax', 'applyTime', 'status', 'result', 'reason', 'location', 'company', 'currentRole', 'tags', 'experience', 'remark', 'nextStep', 'nextContactAt']
  const required = new Set(['name', 'source', 'owner'])
  const headers = keys.map(key => {
    const label = IMPORT_FIELDS.find(field => field.key === key).label
    if (required.has(key)) return `${label} *必填`
    if (['phone', 'wechat', 'email'].includes(key)) return `${label}（至少填一项）`
    return label
  })
  downloadCSV(stringifyCSV([headers]), '人才管理-人才导入模板.csv')
}
