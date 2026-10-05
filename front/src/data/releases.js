import { computed, ref } from 'vue'
import { getReleases } from '@/api/releases'

export const releases = ref([])
export const releaseError = ref('')
export const currentRelease = computed(() => releases.value[0] || {
  version: '—', date: '', updatedAt: '', title: '正在读取版本', summary: '正在从数据库读取版本更新内容。', changes: []
})

let pending
export async function loadReleases(force = false) {
  if (releases.value.length && !force) return releases.value
  if (pending && !force) return pending
  releaseError.value = ''
  pending = getReleases().then(data => {
    releases.value = Array.isArray(data) ? data : []
    return releases.value
  }).catch(error => {
    releaseError.value = error.message || '版本信息读取失败'
    throw error
  }).finally(() => { pending = null })
  return pending
}
