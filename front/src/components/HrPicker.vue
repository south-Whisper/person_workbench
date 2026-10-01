<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  employees: { type: Array, default: () => [] },
  valueKey: { type: String, default: 'name' },
  placeholder: { type: String, default: '搜索并选择 HR' },
  disabled: Boolean
})
const emit = defineEmits(['update:modelValue'])
const open = ref(false)
const query = ref('')
let closeTimer
const selected = computed(() => props.employees.find(item => String(item[props.valueKey]) === String(props.modelValue)))
const matches = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  const rows = !keyword ? props.employees : props.employees.filter(item => [item.name, item.department, item.title].some(value => String(value || '').toLocaleLowerCase().includes(keyword)))
  return [...rows].sort((a, b) => {
    const aStarts = String(a.name || '').toLocaleLowerCase().startsWith(keyword) ? 0 : 1
    const bStarts = String(b.name || '').toLocaleLowerCase().startsWith(keyword) ? 0 : 1
    return aStarts - bStarts || String(a.name).localeCompare(String(b.name), 'zh-CN')
  }).slice(0, 30)
})
function focus() { if (props.disabled) return; query.value = ''; open.value = true; clearTimeout(closeTimer) }
function blur() { closeTimer = setTimeout(() => { open.value = false }, 140) }
function choose(item) { emit('update:modelValue', item[props.valueKey]); query.value = ''; open.value = false }
onBeforeUnmount(() => clearTimeout(closeTimer))
</script>

<template>
  <div class="hr-picker" :class="{ disabled }">
    <input
      :value="open ? query : (selected?.name || '')"
      :placeholder="selected?.name || placeholder"
      :disabled="disabled"
      autocomplete="off"
      role="combobox"
      :aria-expanded="open"
      @focus="focus"
      @blur="blur"
      @input="query = $event.target.value; open = true"
    />
    <span class="hr-arrow">⌄</span>
    <div v-if="open" class="hr-options" role="listbox">
      <button v-for="item in matches" :key="item.id" type="button" :class="{ selected: String(item[valueKey]) === String(modelValue) }" @mousedown.prevent="choose(item)">
        <span><b>{{ item.name }}</b><small>{{ item.department || '人才管理' }} · {{ item.title || 'HR' }}</small></span>
        <em v-if="item.current">当前账号</em>
      </button>
      <p v-if="!matches.length">没有匹配的 HR</p>
    </div>
  </div>
</template>

<style scoped>
.hr-picker{position:relative;width:100%}.hr-picker>input{width:100%;min-height:43px;padding:11px 38px 11px 13px;border:1px solid #d9dce3;border-radius:12px;background:#fff;color:#1f2329;outline:0}.hr-picker>input:focus{border-color:#3370ff;box-shadow:0 0 0 3px rgba(51,112,255,.12)}.hr-picker.disabled>input{background:#f2f3f5;color:#8f959e}.hr-arrow{position:absolute;right:13px;top:12px;color:#8f959e;pointer-events:none}.hr-options{position:absolute;z-index:80;left:0;right:0;top:calc(100% + 6px);max-height:260px;overflow:auto;padding:6px;background:#fff;border:1px solid #dee0e3;border-radius:12px;box-shadow:0 16px 45px rgba(31,35,41,.16)}.hr-options button{display:flex;justify-content:space-between;align-items:center;width:100%;border:0;background:#fff;border-radius:8px;padding:10px;text-align:left;color:#4e5969}.hr-options button:hover,.hr-options button.selected{background:#e8f0ff;color:#245bdb}.hr-options b,.hr-options small{display:block}.hr-options b{font-size:12px}.hr-options small{margin-top:4px;font-size:9px;color:#8f959e}.hr-options em{font-style:normal;font-size:9px;color:#3370ff}.hr-options p{margin:0;padding:16px;text-align:center;color:#8f959e;font-size:11px}
</style>
