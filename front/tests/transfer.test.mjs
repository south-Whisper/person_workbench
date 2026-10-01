import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCandidateExport, escapeCSVCell, inferColumnMapping, parseCSVDocument, prepareImportRows, stringifyCSV } from '../src/utils/transfer.js'
import { findProvinceByLocation, formatLocation } from '../src/data/chinaCities.js'

test('CSV parser preserves quoted commas, escaped quotes and multiline cells', () => {
  const parsed = parseCSVDocument('\uFEFF姓名,备注,手机\r\n张三,"聊过, 很合适",13900000000\r\n李四,"第一行\n第二行且有""引号""",13800000000')
  assert.deepEqual(parsed.headers, ['姓名', '备注', '手机'])
  assert.equal(parsed.rows.length, 2)
  assert.equal(parsed.rows[0].values[1], '聊过, 很合适')
  assert.equal(parsed.rows[1].values[1], '第一行\n第二行且有"引号"')
  assert.equal(parsed.rows[1].sourceRow, 3)
})

test('CSV parser rejects an unclosed quoted cell', () => {
  assert.throws(() => parseCSVDocument('姓名,备注\n张三,"没有结束'), /引号没有闭合/)
})

test('column inference understands Chinese aliases', () => {
  const mapping = inferColumnMapping(['候选人', '手机号', '应聘岗位', '未入职原因'])
  assert.equal(mapping.name, 0)
  assert.equal(mapping.phone, 1)
  assert.equal(mapping.job, 2)
  assert.equal(mapping.reason, 3)
})

test('import preview validates salary and maps a unique job id', () => {
  const parsed = parseCSVDocument('姓名,手机,岗位,最低薪资,最高薪资,状态,结果,原因\n张三,13900000000,摄影师,15,20,已关闭,公司淘汰,薪资未达成一致')
  const [row] = prepareImportRows(parsed, inferColumnMapping(parsed.headers), { positions: [{ id: 7, name: '摄影师', status: '招聘中' }], defaultOwner: 'admin', importBatch: 'batch-test-001' })
  assert.deepEqual(row.errors, [])
  assert.equal(row.payload.jobId, 7)
  assert.equal(row.payload.salaryMin, 15)
  assert.equal(row.payload.reason, '薪资未达成一致')
  assert.equal(row.payload.importRow, 2)
})

test('import preview identifies duplicates inside one file', () => {
  const parsed = parseCSVDocument('姓名,手机\n张三,13900000000\n张三备用,13900000000')
  const rows = prepareImportRows(parsed, inferColumnMapping(parsed.headers), { defaultOwner: 'admin', importBatch: 'batch-test-002' })
  assert.equal(rows[0].warnings.length, 0)
  assert.match(rows[1].warnings[0], /与文件第 2 行手机相同/)
})

test('CSV export neutralizes spreadsheet formulas and excludes sensitive fields', () => {
  assert.ok(escapeCSVCell('=2+2').startsWith("'="))
  const csv = buildCandidateExport([{ id: 1, name: '=2+2', phone: '13900000000', salaryMin: 15, source: '内推' }])
  assert.match(csv, /'=2\+2/)
  assert.doesNotMatch(csv, /13900000000/)
  assert.doesNotMatch(csv, /期望月薪最低值/)
  assert.equal(stringifyCSV([['a,b', 'c']]), '\uFEFF"a,b",c\r\n')
})

test('city cascade preserves municipalities and province-city labels', () => {
  assert.equal(formatLocation('上海市', '上海'), '上海')
  assert.equal(formatLocation('浙江省', '杭州'), '浙江 · 杭州')
  assert.equal(formatLocation('广西壮族自治区', '南宁'), '广西 · 南宁')
  assert.equal(findProvinceByLocation('浙江 · 杭州').province, '浙江省')
})
