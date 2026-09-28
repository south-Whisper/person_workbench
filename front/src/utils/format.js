export const dateTime = value => {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}
export const dateOnly = value => value ? String(value).slice(0, 10) : '—'
export const salary = person => person.salaryMin != null && person.salaryMax != null ? person.salaryMin + ' – ' + person.salaryMax + ' K/月' : person.salary || '待了解'
export const initials = name => Array.from(name || '?').slice(-2).join('')
export const isOverdue = task => task.status !== '已完成' && task.dueAt && new Date(task.dueAt).getTime() < Date.now()
